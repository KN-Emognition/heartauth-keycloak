import {useCopyToClipboard as externalUseCopyToClipboard} from "usehooks-ts";

export default () => {
    const [_, copy] = externalUseCopyToClipboard()
    const handleCopy = (text: string) => () => {
        copy(text)
            .then(() => {
                console.log('Copied!', {text})
            })
            .catch(error => {
                console.error('Failed to copy!', error)
            })
    }
    return {handleCopy}
}
